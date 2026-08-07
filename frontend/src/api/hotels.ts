import { http } from './http'
import type { HotelResponse, PropertyType } from '@/types/hotel'

export const hotelsApi = {
  async getById(id: number | string): Promise<HotelResponse> {
    const { data } = await http.get<HotelResponse>(`/api/hotels/${id}`)
    return data
  },

  async getByCity(city: string): Promise<HotelResponse[]> {
    const { data } = await http.get<HotelResponse[]>(`/api/hotels/city/${encodeURIComponent(city)}`)
    return data
  },

  async getByCountry(country: string): Promise<HotelResponse[]> {
    const { data } = await http.get<HotelResponse[]>(`/api/hotels/country/${encodeURIComponent(country)}`)
    return data
  },

  async getByRating(rating: number): Promise<HotelResponse[]> {
    const { data } = await http.get<HotelResponse[]>(`/api/hotels/rating/${rating}`)
    return data
  },

  async getByCompany(companyId: number): Promise<HotelResponse[]> {
    const { data } = await http.get<HotelResponse[]>(`/api/hotels/company/${companyId}`)
    return data
  },

  async getByType(propertyType: PropertyType): Promise<HotelResponse[]> {
    const { data } = await http.get<HotelResponse[]>(`/api/hotels/type/${propertyType}`)
    return data
  },
}
