import api from "../api";
import { withTokenRefresh } from "../auth/refresh";
// favoriteLocation is { cityName, countryName }; 409 when already saved, 403 when the limit is reached.
export default async function saveFavoriteLocationRequest(favoriteLocation){
    await withTokenRefresh(() => api.post("/api/weather/save-favorite-location",
        favoriteLocation
    ));
}
