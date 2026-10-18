import api from "../api";

// signal (optional) lets the caller abort a request that a newer one has replaced.
export default async function loadWeatherByCordinatesRequest(lat, lon, { signal } = {}){
    const response = await api.get("/api/weather/coordinates", {
        params:{
            lat: lat,
            lon: lon,
        },
        signal,
    });
    return response.data;
}
