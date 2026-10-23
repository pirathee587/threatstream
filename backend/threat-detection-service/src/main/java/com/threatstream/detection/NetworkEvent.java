package com.threatstream.detection;
import java.time.Instant; import java.util.UUID;
public record NetworkEvent(UUID eventId,String eventType,int schemaVersion,Instant timestamp,String sourceIp,String destinationIp,Integer sourcePort,Integer destinationPort,String protocol,Long flowDuration,Long totalForwardPackets,Long totalBackwardPackets,Double flowBytesPerSecond,Double flowPacketsPerSecond,String serviceName) { }
