"""Long-running Kafka inference service. Training is deliberately unavailable here."""
from __future__ import annotations
import json, logging, os, signal, time
from collections import OrderedDict
from pathlib import Path
from typing import Any
import pandas as pd
from .artifacts import load_artifact
from .schema import EventValidationError, FEATURE_COLUMNS, MlResult, validate_network_event

LOG=logging.getLogger("threatstream_ml")

class RecentEvents:
    def __init__(self, limit:int=10_000): self.limit=limit; self.items=OrderedDict()
    def contains(self, event_id:str)->bool:
        return event_id in self.items
    def add(self, event_id:str)->None:
        self.items[event_id]=None
        if len(self.items)>self.limit: self.items.popitem(last=False)

class Predictor:
    def __init__(self, artifact_dir:Path): self.model,self.metadata=load_artifact(artifact_dir)
    def predict(self,event:dict[str,Any])->dict[str,Any]:
        normalized=validate_network_event(event); frame=pd.DataFrame([{column:normalized[column] for column in FEATURE_COLUMNS}],columns=FEATURE_COLUMNS)
        label=str(self.model.predict(frame)[0]); confidence=None
        if hasattr(self.model,"predict_proba"):
            probabilities=self.model.predict_proba(frame)[0]; confidence=round(float(max(probabilities)),6)
        benign=label.casefold()=="benign"
        return MlResult(normalized["eventId"],normalized["timestamp"],normalized["sourceIp"],"BENIGN" if benign else "ATTACK",label,self.metadata["modelVersion"],confidence,normalized.get("correlationId")).as_dict()

def run() -> None:
    from confluent_kafka import Consumer, Producer, KafkaException
    logging.basicConfig(level=os.getenv("ML_LOG_LEVEL","INFO"),format="%(asctime)s %(levelname)s %(name)s %(message)s")
    predictor=Predictor(Path(os.getenv("ML_MODEL_DIR","ml/artifacts/cicids2017-v1")))
    bootstrap=os.getenv("KAFKA_BOOTSTRAP_SERVERS","localhost:9094"); input_topic=os.getenv("KAFKA_TOPIC_NETWORK_EVENTS","security.network.events"); output_topic=os.getenv("KAFKA_TOPIC_ML_ALERTS","security.ml.alerts"); dlt_topic=os.getenv("KAFKA_TOPIC_DLT","security.events.dlt")
    consumer=Consumer({"bootstrap.servers":bootstrap,"group.id":os.getenv("KAFKA_ML_GROUP_ID","ml-detection-service-v1"),"auto.offset.reset":"earliest","enable.auto.commit":False,"enable.partition.eof":False,"session.timeout.ms":10000})
    producer=Producer({"bootstrap.servers":bootstrap,"acks":"all","enable.idempotence":True,"retries":3,"delivery.timeout.ms":30000})
    stopped=False; attempts={}; max_attempts=int(os.getenv("ML_PROCESSING_RETRY_ATTEMPTS","3"))
    def shutdown(*_):
        nonlocal stopped; stopped=True
    signal.signal(signal.SIGINT,shutdown); signal.signal(signal.SIGTERM,shutdown); consumer.subscribe([input_topic]); dedup=RecentEvents(int(os.getenv("ML_DEDUP_CACHE_SIZE","10000")))
    try:
        while not stopped:
            message=consumer.poll(1.0)
            if message is None: continue
            if message.error(): LOG.warning("Kafka consumer error: %s",message.error()); continue
            try:
                event=json.loads(message.value().decode("utf-8")); event_id=str(event.get("eventId","unknown")) if isinstance(event,dict) else "unknown"
                if dedup.contains(event_id): LOG.info("Skipping duplicate ML event %s",event_id)
                else:
                    result=predictor.predict(event); headers=[]
                    correlation=result.get("correlationId")
                    if correlation: headers.append(("correlationId",correlation.encode()))
                    producer.produce(output_topic,key=result["eventId"],value=json.dumps(result),headers=headers); producer.flush(30); dedup.add(event_id)
                consumer.commit(message=message,asynchronous=False)
            except (json.JSONDecodeError,EventValidationError) as error:
                payload={"errorCategory":"VALIDATION_ERROR","error":str(error),"originalTopic":message.topic(),"partition":message.partition(),"offset":message.offset()}
                producer.produce(dlt_topic,key=message.key(),value=json.dumps(payload)); producer.flush(30); consumer.commit(message=message,asynchronous=False); LOG.warning("Rejected ML input: %s",error)
            except Exception as error:
                record_key=(message.topic(),message.partition(),message.offset()); attempts[record_key]=attempts.get(record_key,0)+1
                if attempts[record_key] >= max_attempts:
                    payload={"errorCategory":"PROCESSING_ERROR","error":type(error).__name__,"originalTopic":message.topic(),"partition":message.partition(),"offset":message.offset()}
                    producer.produce(dlt_topic,key=message.key(),value=json.dumps(payload)); producer.flush(30); consumer.commit(message=message,asynchronous=False); attempts.pop(record_key,None)
                    LOG.exception("ML processing failed after bounded retries; routed record to DLT")
                else:
                    LOG.exception("Transient ML processing failure [attempt=%s/%s]; record left uncommitted",attempts[record_key],max_attempts)
    finally:
        consumer.close(); producer.flush(10)

if __name__ == "__main__": run()
