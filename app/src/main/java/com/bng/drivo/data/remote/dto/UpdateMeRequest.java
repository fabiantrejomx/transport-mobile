package com.bng.drivo.data.remote.dto;

public class UpdateMeRequest {
    public String name;
    public String email;
    public String photo_url;

    public UpdateMeRequest(String name, String email, String photoUrl) {
        this.name = name;
        this.email = email;
        this.photo_url = photoUrl;
    }
}
