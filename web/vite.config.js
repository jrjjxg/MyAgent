import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
const apiTarget = process.env.VITE_API_TARGET || "http://localhost:8080";
export default defineConfig({
    plugins: [react()],
    server: {
        host: "0.0.0.0",
        port: 5173,
        proxy: {
            "/workspaces": {
                target: apiTarget,
                changeOrigin: true
            },
            "/threads": {
                target: apiTarget,
                changeOrigin: true
            },
            "/memory": {
                target: apiTarget,
                changeOrigin: true
            },
            "/skills": {
                target: apiTarget,
                changeOrigin: true
            },
            "/model-settings": {
                target: apiTarget,
                changeOrigin: true
            },
            "/web-settings": {
                target: apiTarget,
                changeOrigin: true
            }
        }
    },
    preview: {
        host: "0.0.0.0",
        port: 4173
    }
});
