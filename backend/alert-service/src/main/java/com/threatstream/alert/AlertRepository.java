package com.threatstream.alert;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends MongoRepository<AlertDocument, String> {
  Optional<AlertDocument> findByAlertId(UUID alertId);
  long countByStatus(String status);
  boolean existsByAlertId(UUID alertId);
}
