package com.aquaassistant.place;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Gives "Mi agua" to an account that ended up without any place, in a transaction of its own. */
@Component
public class PlaceProvisioner {

    private final PlaceAccess access;

    public PlaceProvisioner(PlaceAccess access) {
        this.access = access;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Place provisionAlone(Long userId) {
        return access.provision(userId);
    }
}
