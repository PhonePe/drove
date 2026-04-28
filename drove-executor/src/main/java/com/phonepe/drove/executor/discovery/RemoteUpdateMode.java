package com.phonepe.drove.executor.discovery;

/**
 * Represents how executor will send updates to controller
 */
public enum RemoteUpdateMode {
    /**
     * Direct store update
     */
    STORE,
    /**
     * Send update over HTTP(S), in case of failure, send over store
     */
    RPC
}
