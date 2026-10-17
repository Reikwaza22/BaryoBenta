package com.baryobenta.community;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class WalletData {
    private double balance;
    @SerializedName("posting_fee") private double postingFee;
    private List<LedgerEntry> ledger;
    @SerializedName("topup_requests") private List<TopupRequest> topupRequests;
    @SerializedName("payment_instructions") private String paymentInstructions;
    @SerializedName("minimum_topup") private double minimumTopup;
    @SerializedName("maximum_topup") private double maximumTopup;

    public double getBalance() { return balance; }
    public double getPostingFee() { return postingFee > 0 ? postingFee : 10; }
    public List<LedgerEntry> getLedger() { return ledger == null ? List.of() : ledger; }
    public List<TopupRequest> getTopupRequests() { return topupRequests == null ? List.of() : topupRequests; }
    public String getPaymentInstructions() { return paymentInstructions == null ? "" : paymentInstructions; }
    public double getMinimumTopup() { return minimumTopup; }
    public double getMaximumTopup() { return maximumTopup; }

    public static class TopupRequest {
        private double amount;
        @SerializedName("payment_method") private String paymentMethod;
        @SerializedName("payment_reference") private String paymentReference;
        private String status;
        @SerializedName("created_at") private String createdAt;

        public double getAmount() { return amount; }
        public String getPaymentMethod() { return paymentMethod == null ? "" : paymentMethod; }
        public String getPaymentReference() { return paymentReference == null ? "" : paymentReference; }
        public String getStatus() { return status == null ? "pending" : status; }
        public String getCreatedAt() { return createdAt; }
    }

    public static class LedgerEntry {
        private double amount;
        private String kind;
        private String note;
        @SerializedName("created_at") private String createdAt;

        public double getAmount() { return amount; }
        public String getKind() { return kind; }
        public String getNote() { return note; }
        public String getCreatedAt() { return createdAt; }

        public boolean isCredit() { return amount >= 0; }
    }
}
