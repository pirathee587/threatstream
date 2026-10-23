"""Chunked, label-safe CIC-IDS2017 network-flow replay."""
from __future__ import annotations
import argparse, json, math, os, time, uuid
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Iterable
import pandas as pd

REQUIRED={"sourceip","destinationip","sourceport","destinationport","protocol","timestamp"}
FEATURES={"flowduration","totalforwardpackets","totalbackwardpackets","flowbytespersecond","flowpacketspersecond"}
ALIASES={"sourceip":("sourceip",),"destinationip":("destinationip",),"sourceport":("sourceport",),"destinationport":("destinationport",),"protocol":("protocol",),"timestamp":("timestamp",),"flowduration":("flowduration",),"totalforwardpackets":("totalforwardpackets","totfwdpkts"),"totalbackwardpackets":("totalbackwardpackets","totbwdpkts"),"flowbytespersecond":("flowbytes/s","flowbytespersecond"),"flowpacketspersecond":("flowpackets/s","flowpacketspersecond"),"label":("label",)}
OUT={"flowduration":"flowDuration","totalforwardpackets":"totalForwardPackets","totalbackwardpackets":"totalBackwardPackets","flowbytespersecond":"flowBytesPerSecond","flowpacketspersecond":"flowPacketsPerSecond"}
def dataset_directory()->Path:return Path(__file__).resolve().parents[2]/"data"/"CIC-IDS2017"
def normalize_column(name:str)->str:return "".join(str(name).strip().lower().split())
def column_mapping(columns:Iterable[str])->dict[str,str]:
 n={normalize_column(x):str(x) for x in columns};return {f:next((n[a] for a in v if a in n),"") for f,v in ALIASES.items()}
@dataclass
class Summary:
 processed:int=0;published:int=0;skipped:int=0;malformed_rows:int=0;columns:set[str]=field(default_factory=set);labels:Counter[str]=field(default_factory=Counter)
def _finite(value:Any, integer:bool=False)->int|float|None:
 try:
  n=float(str(value).strip());return (int(n) if integer else n) if math.isfinite(n) and (not integer or n>=0 and n.is_integer()) else None
 except (TypeError,ValueError):return None
def event_from_row(row:pd.Series,m:dict[str,str])->dict[str,Any]|None:
 if any(not m.get(f) or not str(row[m[f]]).strip() for f in REQUIRED):return None
 ts=pd.to_datetime(row[m["timestamp"]],utc=True,errors="coerce");ports=[_finite(row[m[f]],True) for f in ("sourceport","destinationport")]
 if pd.isna(ts) or any(x is None or x>65535 for x in ports):return None
 e={"eventId":str(uuid.uuid4()),"eventType":"NETWORK_FLOW","schemaVersion":1,"timestamp":ts.isoformat().replace("+00:00","Z"),"sourceIp":str(row[m["sourceip"]]).strip(),"destinationIp":str(row[m["destinationip"]]).strip(),"sourcePort":ports[0],"destinationPort":ports[1],"protocol":str(row[m["protocol"]]).strip(),"serviceName":"cic-ids2017-replay"}
 for f in FEATURES:
  if m.get(f):
   v=_finite(row[m[f]],f in {"flowduration","totalforwardpackets","totalbackwardpackets"})
   if v is None:return None
   e[OUT[f]]=v
 return e
class KafkaPublisher:
 def __init__(self,bootstrap:str,topic:str)->None:
  from confluent_kafka import Producer
  self.topic,self.failed=topic,[];self.producer=Producer({"bootstrap.servers":bootstrap,"acks":"all","retries":3,"delivery.timeout.ms":30000})
 def publish(self,event:dict[str,Any])->None:self.producer.produce(self.topic,key=event["eventId"],value=json.dumps(event),callback=self._delivery);self.producer.poll(0)
 def _delivery(self,error:Any,_:Any)->None:
  if error:self.failed.append(str(error))
 def close(self)->None:
  remaining=self.producer.flush(30)
  if remaining or self.failed:raise RuntimeError(f"Kafka delivery failed: {self.failed[:3] or [str(remaining)+' undelivered messages']}")
def replay(path:Path,*,chunk_size:int=10_000,max_events:int|None=None,rate:float=0,dry_run:bool=False,publisher_factory:Callable[[],Any]|None=None)->Summary:
 files=[path] if path.is_file() else sorted(path.rglob("*.csv"))
 if not files:raise FileNotFoundError(f"No CSV files found under {path}")
 s,publisher=Summary(),None
 for file in files:
  bad=[0]
  def malformed(_:list[str])->None:bad[0]+=1
  for chunk in pd.read_csv(file,chunksize=chunk_size,dtype=str,on_bad_lines=malformed,engine="python"):
   m=column_mapping(chunk.columns);s.columns.update(map(str,chunk.columns));s.malformed_rows+=bad[0];bad[0]=0
   if m["label"]:s.labels.update(chunk[m["label"]].fillna("<missing>").str.strip())
   for _,row in chunk.iterrows():
    s.processed+=1;e=event_from_row(row,m)
    if e is None:s.skipped+=1;continue
    if not dry_run:
     publisher=publisher or (publisher_factory() if publisher_factory else KafkaPublisher(os.getenv("KAFKA_BOOTSTRAP_SERVERS","localhost:9094"),os.getenv("KAFKA_TOPIC_NETWORK_EVENTS","security.network.events")));publisher.publish(e)
    s.published+=1
    if max_events and s.published>=max_events:
     if publisher:publisher.close()
     return s
    if rate>0:time.sleep(1/rate)
 if publisher:publisher.close()
 return s
def main()->None:
 p=argparse.ArgumentParser(description="Replay CIC-IDS2017 CSV flows without labels.");p.add_argument("path",type=Path);p.add_argument("--chunk-size",type=int,default=10000);p.add_argument("--max-events",type=int);p.add_argument("--rate",type=float,default=0);p.add_argument("--dry-run",action="store_true");a=p.parse_args();s=replay(a.path,chunk_size=a.chunk_size,max_events=a.max_events,rate=a.rate,dry_run=a.dry_run);print(json.dumps({**s.__dict__,"columns":sorted(s.columns),"labels":dict(s.labels)},default=str))
if __name__=="__main__":main()
