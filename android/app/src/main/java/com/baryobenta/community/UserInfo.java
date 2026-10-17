package com.baryobenta.community;

import com.google.gson.annotations.SerializedName;

/** The signed-in user as returned by login.php / register.php. */
public class UserInfo {
    @SerializedName("user_id") private int userId;
    @SerializedName("full_name") private String fullName;
    private String username;
    private String email;
    private String location;
    private String role;
    private boolean verified;
    @SerializedName("kyc_status") private String kycStatus;

    public int getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getLocation() { return location; }
    public String getRole() { return role; }
    public boolean isVerified() { return verified; }
    public String getKycStatus() { return kycStatus; }
}
