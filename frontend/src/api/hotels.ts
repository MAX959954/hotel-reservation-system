import { http } from './http'
import type { HotelResponse } from '../types/hotel'

export const hotelsApi = {
  getById(id: number) {
    return http.get<HotelResponse>(`/api/hotels/${id}`).then((r) => r.data)
  },
  getByCity(city: string) {
    return http.get<HotelResponse[]>(`/api/hotels/city/${city}`).then((r) => r.data)
  },
  getByCountry(country: string) {
    return http.get<HotelResponse[]>(`/api/hotels/country/${country}`).then((r) => r.data)
  },
  getByCompany(companyId: number) {
    return http.get<HotelResponse[]>(`/api/hotels/company/${companyId}`).then((r) => r.data)
  },
  getByRating(rating: number) {
    return http.get<HotelResponse[]>(`/api/hotels/rating/${rating}`).then((r) => r.data)
  },
}
