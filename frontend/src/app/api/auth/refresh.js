import api from "../api";
export default async function refreshTokenRequest(){
    await api.post("/api/auth/refresh");
}

// The access token lives 15 minutes: on a 401, refresh the session once and repeat the request.
export async function withTokenRefresh(request){
    try {
        return await request();
    } catch (e) {
        if (e.response?.status !== 401) {
            throw e;
        }
        await refreshTokenRequest();
        return request();
    }
}
