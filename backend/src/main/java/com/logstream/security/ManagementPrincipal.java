package com.logstream.security;

public record ManagementPrincipal(String email, String subject, String name, String provider) {
}
