import { axiosClient } from '@/api/axiosClient'
import type { TrafficBucket, TrafficSnapshot } from '@/types'

export const trafficApi = {
  async snapshot(bucket: TrafficBucket): Promise<TrafficSnapshot> {
    const { data } = await axiosClient.get<TrafficSnapshot>(
      '/api/traffic/snapshot',
      { params: { bucket } },
    )
    return data
  },
}
