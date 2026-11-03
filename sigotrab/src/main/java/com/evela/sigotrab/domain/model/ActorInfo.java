package com.evela.sigotrab.domain.model;

import java.util.UUID;

public record ActorInfo(UUID userId, String username, String ipAddress) {

    public static ActorInfo system(){
        return new ActorInfo(null, "SYSTEM", null);
    }

}
