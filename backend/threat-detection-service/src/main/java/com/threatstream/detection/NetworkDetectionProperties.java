package com.threatstream.detection;
import java.time.Duration; import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("network-detection") public record NetworkDetectionProperties(String topic,int maxTrackedKeys,PortScan portScan,Rate dos,Distributed ddos){public record PortScan(int threshold,Duration window,Duration cooldown){} public record Rate(double bytesPerSecond,double packetsPerSecond,Duration window,Duration cooldown){} public record Distributed(double bytesPerSecond,int sources,Duration window,Duration cooldown){}}
