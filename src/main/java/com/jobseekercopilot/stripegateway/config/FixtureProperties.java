package com.jobseekercopilot.stripegateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "fixture")
public class FixtureProperties {
    private String systemDataServiceUrl = "http://localhost:8103";
    private String datasetId = "uk-software-developer-demo";
    private String datasetVersion = "1.0.0";
    private String scenario = "happy-path";
    private boolean paymentControlEnabled;
    private String paymentControlToken;
    private String webhookSecret;

    public String getSystemDataServiceUrl() {
        return systemDataServiceUrl;
    }

    public void setSystemDataServiceUrl(String systemDataServiceUrl) {
        this.systemDataServiceUrl = systemDataServiceUrl;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getDatasetVersion() {
        return datasetVersion;
    }

    public void setDatasetVersion(String datasetVersion) {
        this.datasetVersion = datasetVersion;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public boolean isPaymentControlEnabled() {
        return paymentControlEnabled;
    }

    public void setPaymentControlEnabled(boolean paymentControlEnabled) {
        this.paymentControlEnabled = paymentControlEnabled;
    }

    public String getPaymentControlToken() {
        return paymentControlToken;
    }

    public void setPaymentControlToken(String paymentControlToken) {
        this.paymentControlToken = paymentControlToken;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}
