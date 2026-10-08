package com.smartbanking.fraud.repository;

import com.smartbanking.fraud.model.FraudFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FraudFlagRepository extends JpaRepository<FraudFlag, Long> {
    List<FraudFlag> findByResolvedFalseOrderByFlaggedAtDesc();
}
