package com.threatstream.detection;
import java.util.concurrent.TimeUnit; import org.springframework.kafka.core.KafkaTemplate; import org.springframework.stereotype.Component;
@Component class AlertPublisher { private final KafkaTemplate<String,SecurityAlert> kafka; private final DetectionProperties p; AlertPublisher(KafkaTemplate<String,SecurityAlert> k,DetectionProperties p){kafka=k;this.p=p;} void publish(SecurityAlert a){try{kafka.send(p.alertTopic(),a.alertId().toString(),a).get(15,TimeUnit.SECONDS);}catch(Exception e){throw new AlertPublishException();}} }
