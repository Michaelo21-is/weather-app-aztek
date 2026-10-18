import api from "../api";
export default async function signInRequest(form){
    await api.post("/api/auth/sign-in", form);
}
