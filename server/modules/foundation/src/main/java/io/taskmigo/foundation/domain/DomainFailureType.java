package io.taskmigo.foundation.domain;

/// Classifies transport-neutral domain failures shared across business capabilities.
public enum DomainFailureType {
    BAD_REQUEST,
    NOT_FOUND,
    CONFLICT,
}
