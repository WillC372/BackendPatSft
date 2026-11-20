package com.aquaassistant.measurement;

import com.aquaassistant.common.BusinessException;
import com.aquaassistant.user.Role;
import com.aquaassistant.user.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Who records pH measurements: the user accounts. The administrator works for the company that runs the platform
 * (accounts, plans, payments, AI usage, official data) and does not measure water in any way: by hand, with a meter,
 * with a photo or loading official data into a place.
 */
@Component
public class Measurers {

    public static final String ADMIN_DOES_NOT_MEASURE = "El administrador gestiona la plataforma y no registra mediciones de pH: "
        + "eso lo hacen las cuentas de usuario.";

    private final UserRepository users;

    public Measurers(UserRepository users) {
        this.users = users;
    }

    public void require(Long userId) {
        if (users.findById(userId).map(u -> u.getRole() == Role.ADMIN).orElse(false)) {
            throw BusinessException.forbidden(ADMIN_DOES_NOT_MEASURE);
        }
    }
}
