import api from "../api";
import { withTokenRefresh } from "../auth/refresh";
// axios.delete takes the request body as config.data; 404 when the location isn't saved.
export default async function removeFavoriteLocationRequest(favoriteLocation){
    await withTokenRefresh(() => api.delete("/api/weather/remove-favorite-location", {
        data: favoriteLocation,
    }));
}
