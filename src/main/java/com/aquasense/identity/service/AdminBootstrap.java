package com.aquasense.identity.service;

import com.aquasense.identity.domain.RoleName;
import com.aquasense.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final String adminEmail;

    public AdminBootstrap(UserRepository users, @Value("${aquasense.admin-email:}") String adminEmail) {
        this.users = users;
        this.adminEmail = adminEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }
        users.findByIdentifier(adminEmail.trim()).ifPresentOrElse(user -> {
            if (!user.getRoles().contains(RoleName.ADMIN)) {
                user.grantRole(RoleName.ADMIN);
                users.save(user);
                LOG.info("Rol ADMIN concedido al usuario configurado en ADMIN_EMAIL.");
            }
        }, () -> LOG.warn("ADMIN_EMAIL está definido pero ese usuario aún no existe: regístrelo y reinicie."));
    }
}
