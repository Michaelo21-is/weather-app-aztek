import api from "../api";
// Returns up to 5 { name, country } matches; the backend rejects queries shorter than 2 characters with 400.
export default async function citySuggestionQueryRequest(query, { signal } = {}){
    const response = await api.get("/api/weather/city-suggestion",{
        params:{
            query:query
        },
        signal,
    });
    return response.data;
}
