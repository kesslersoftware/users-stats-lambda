package com.boycottpro.users.model;

public class ResponsePojo {

    private int totalBoycotts;
    private int numCausesFollowed;
    private String worstCompanyName;
    private int worstCount;
    private String topReason;
    private String causeName;
    private int followerCount;

    public ResponsePojo(int totalBoycotts, int numCausesFollowed, String worstCompanyName,
                        int worstCount, String topReason, String causeName, int followerCount) {
        this.totalBoycotts = totalBoycotts;
        this.numCausesFollowed = numCausesFollowed;
        this.worstCompanyName = worstCompanyName;
        this.worstCount = worstCount;
        this.topReason = topReason;
        this.causeName = causeName;
        this.followerCount = followerCount;
    }

    public int getTotalBoycotts() {
        return totalBoycotts;
    }

    public int getNumCausesFollowed() {
        return numCausesFollowed;
    }

    public String getWorstCompanyName() {
        return worstCompanyName;
    }

    public int getWorstCount() {
        return worstCount;
    }

    public String getTopReason() {
        return topReason;
    }

    public String getCauseName() {
        return causeName;
    }

    public int getFollowerCount() {
        return followerCount;
    }
}
