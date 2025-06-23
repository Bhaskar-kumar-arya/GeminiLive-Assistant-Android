# TURN/STUN Server Setup Summary

This document summarizes the current state of the TURN/STUN server setup on your sub-linux system and outlines the next steps for completion.

## Current State

*   The `coturn` TURN/STUN server has been successfully installed on your sub-linux system (WSL).
*   The main configuration file (`/etc/turnserver.conf`) has been located and edited.
*   The `realm` has been set to `gamesmith.com`.
*   A `static-auth-secret` (`YOUR_STATIC_AUTH_SECRET`) has been configured for authentication.
*   The `coturn` service has been restarted and is running.
*   The server is listening on default ports (3478 for UDP/TCP) on its configured IP addresses.

## Next Steps

1.  **Firewall Configuration:** (Completed)
    *   Configure your firewall to allow incoming UDP and TCP traffic on port 3478.
    *   If you plan to use TLS/DTLS, also allow incoming TCP and UDP traffic on port 5349.
    *   Forward these ports to the internal IP address of your WSL instance (e.g., `YOUR_WSL_INTERNAL_IP`).

2.  **TLS/DTLS Configuration (Recommended for Production):**
    *   Obtain SSL/TLS certificates (e.g., from Let's Encrypt or a commercial CA).
    *   Update the `coturn` configuration file (`/etc/turnserver.conf`) to specify the paths to your certificate (`cert`) and private key (`pkey`) files.
    *   Restart the `coturn` service after updating the configuration.

3.  **Client Integration:** (Completed)
    *   Configure your WebRTC client to use the TURN/STUN server.
    *   Use the appropriate server URLs, replacing `your_server_ip` with the public IP address of your server (which is forwarded to your WSL instance):
        *   `turn:your_server_ip:3478?transport=udp`
        *   `turn:your_server_ip:3478?transport=tcp`
        *   `stun:your_server_ip:3478`
        *   If TLS/DTLS is configured: `turns:your_server_ip:5349?transport=tcp`, `turn:your_server_ip:5349?transport=udp`
    *   Set the `realm` in your client configuration to `gamesmith.com`.
    *   Implement the authentication mechanism in your client using the `static-auth-secret` (`YOUR_STATIC_AUTH_SECRET`) to generate temporary username and password credentials (typically using a timestamp as the username and an HMAC-SHA1 hash as the password).

4.  **Managing the coturn Service:**
    *   The `coturn` server runs as a background service on your WSL instance. You can manage it using the `service` command with `sudo`.
    *   **Check Status:** `sudo service coturn status`
    *   **Start:** `sudo service coturn start`
    *   **Stop:** `sudo service coturn stop`
    *   **Restart:** `sudo service coturn restart` (Use this after editing `/etc/turnserver.conf`)

This document provides a roadmap for completing the TURN/STUN server setup and integrating it with your client application.