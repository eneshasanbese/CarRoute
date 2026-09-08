import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/components/AppLayout'
import { Skeleton } from '@/components/ui/skeleton'
import { Toaster } from '@/components/ui/sonner'
import { Dashboard } from '@/pages/Dashboard'
import { PersonelYonetimi } from '@/pages/PersonelYonetimi'

// Harita sayfaları Leaflet'i de yükler; ilk açılışı yavaşlatmamak için ayrı chunk.
const ServisDetay = lazy(() =>
  import('@/pages/ServisDetay').then((m) => ({ default: m.ServisDetay })),
)
const HaritaGenel = lazy(() =>
  import('@/pages/HaritaGenel').then((m) => ({ default: m.HaritaGenel })),
)

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 3_000,
      retry: 1,
      refetchOnWindowFocus: true,
    },
  },
})

function PageFallback() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-8 w-52" />
      <Skeleton className="h-[560px] w-full rounded-xl" />
    </div>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Suspense fallback={<PageFallback />}>
          <Routes>
            <Route element={<AppLayout />}>
              <Route index element={<Dashboard />} />
              <Route path="personel" element={<PersonelYonetimi />} />
              <Route path="servis/:id" element={<ServisDetay />} />
              <Route path="harita" element={<HaritaGenel />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        </Suspense>
      </BrowserRouter>
      <Toaster />
    </QueryClientProvider>
  )
}
