import api from "../api";
export default async function signUpRequest(form){
    await api.post("/api/auth/sign-up", form);
}
