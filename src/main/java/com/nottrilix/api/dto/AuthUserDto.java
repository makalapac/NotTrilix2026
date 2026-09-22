package com.nottrilix.api.dto;

import com.nottrilix.auth.AuthUser;
import com.nottrilix.auth.Realm;

public record AuthUserDto(Realm realm, Long id, String name, String login) {

    public static AuthUserDto from(AuthUser user) {
        return new AuthUserDto(user.realm(), user.id(), user.name(), user.login());
    }
}
