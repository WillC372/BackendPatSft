package com.aquaassistant.i18n;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UiTranslationRepository extends JpaRepository<UiTranslation, String> {

    List<UiTranslation> findByLang(String lang);

    long countByLang(String lang);
}
