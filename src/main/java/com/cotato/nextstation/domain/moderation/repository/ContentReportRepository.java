package com.cotato.nextstation.domain.moderation.repository;

import com.cotato.nextstation.domain.moderation.entity.ContentReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {
}
