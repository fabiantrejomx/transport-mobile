package com.bng.drivo.data.model;

public class UserProfile {

    private final String id;
    private String name;
    private String email;
    private final String phone;
    private String photoUrl;
    private final Double rating;
    private final Integer trips;

    public UserProfile(String id, String name, String email, String phone, String photoUrl,
                       Double rating, Integer trips) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.photoUrl = photoUrl;
        this.rating = rating;
        this.trips = trips;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    /**
     * Promedio de estrellas, o null si el backend todavía no lo manda — ver {@code MeDto#rating}.
     * Null significa "no lo sé", nunca "no tiene": quien lo pinte no debe rellenarlo con un 5.0.
     */
    public Double getRating() {
        return rating;
    }

    /** Viajes que cuentan para el promedio; null si no viene. Cero significa usuario nuevo. */
    public Integer getTrips() {
        return trips;
    }

    public boolean isComplete() {
        return name != null && !name.trim().isEmpty();
    }

    public String getInitials() {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < parts.length && initials.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                initials.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return initials.toString();
    }
}
