import api from "../api";
export default async function signOutRequest(){
    await api.post("/api/auth/sign-out");
}