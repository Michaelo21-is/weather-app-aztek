import api from "../api";
import { withTokenRefresh } from "../auth/refresh";
// Returns [{ cityName, countryName, weather }], where weather is the current WeatherResponse for that city.
export default async function getUserFavoriteLocationRequest(){
    return withTokenRefresh(async () => {
        const response = await api.get("/api/weather/get-user-favorite-location");
        return response.data;
    });
}
