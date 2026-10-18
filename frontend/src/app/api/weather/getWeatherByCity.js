import api from "../api";
// countryName is the ISO 3166 alpha-2 code returned by the city suggestions (e.g. "GB").
export default async function getWeatherByCityRequest(cityName, countryName, { signal } = {}){
    const response = await api.get("/api/weather/city",{
        params:{
            cityName:cityName,
            countryName:countryName,
        },
        signal,
    });
    return response.data;
}
