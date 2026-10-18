import api from "../api";
import refreshTokenRequest from "../auth/refresh";
export default async function getNicknameRequest() {
    try {
        const response = await api.get("/api/user/get-nickname");
        return response.data;
    } catch (e) {
        if (e.response?.status !== 401) {
            throw e;
        }

        await refreshTokenRequest();

        const response = await api.get("/api/user/get-nickname");
        return response.data;
    }
}