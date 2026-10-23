package com.threatstream.detection;
import java.util.Optional;
interface DetectionRule { Optional<SecurityAlert> evaluate(AuthEvent event); }
