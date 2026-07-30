import { http } from './http'
import type { ReviewResponse } from '../types/review'

export const reviewsApi = {
  getApprovedByRoom(roomId: number) {
    return http.get<ReviewResponse[]>(`/api/reviews/room/${roomId}/approved`).then((r) => r.data)
  },
  getAverageRatingByRoom(roomId: number) {
    return http.get<number>(`/api/reviews/room/${roomId}/rating`).then((r) => r.data)
  },
}
