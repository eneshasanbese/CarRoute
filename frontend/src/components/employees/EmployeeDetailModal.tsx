import * as DialogPrimitive from '@radix-ui/react-dialog'
import {
  BabyIcon,
  CarIcon,
  Loader2Icon,
  MapPinIcon,
  MoonIcon,
  PhoneIcon,
  SunIcon,
  XIcon,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { servicesApi } from '@/api/servicesApi'
import { LocationMiniMap } from '@/components/map/LocationMiniMap'
import { Badge } from '@/components/ui/badge'
import { useServiceColor } from '@/hooks/useServiceColor'
import { useAppSelector } from '@/store/hooks'
import { selectServiceNames } from '@/store/selectors'
import type { Employee, RouteStop, Sefer } from '@/types'

/** Bir seferde bu kişinin durağı; servisi yoksa ya da yüklenemediyse null. */
type Durak = RouteStop | null

interface EmployeeDetailModalProps {
  employee: Employee | null
  onOpenChange: (open: boolean) => void
}

/**
 * Personel kartı: kişi bilgileri, servisi, iki seferdeki kapı önü saatleri ve
 * konumu.
 *
 * <p>
 * Saatler store'dan değil doğrudan API'den çekiliyor. Sebep: servicesSlice
 * rotaları yalnızca <em>görüntülenen</em> sefer için önbelleğe alıyor; buraya iki
 * sefer birden gerekiyor ve ikisini o önbelleğe yazmak onu bozardı.
 */
export function EmployeeDetailModal({
  employee,
  onOpenChange,
}: EmployeeDetailModalProps) {
  const [sabah, setSabah] = useState<Durak>(null)
  const [aksam, setAksam] = useState<Durak>(null)
  const [yukleniyor, setYukleniyor] = useState(false)
  const [hata, setHata] = useState<string | null>(null)
  const servisAdlari = useAppSelector(selectServiceNames)
  const serviceColor = useServiceColor()

  const servisId = employee?.servisId ?? null
  const employeeId = employee?.id ?? null

  useEffect(() => {
    if (employeeId == null || servisId == null) {
      setSabah(null)
      setAksam(null)
      return
    }

    let iptal = false
    setYukleniyor(true)
    setHata(null)

    const duragiBul = (stops: RouteStop[]) =>
      stops.find((stop) => stop.employeeId === employeeId) ?? null

    Promise.all([
      servicesApi.route(servisId, 'sabah' satisfies Sefer),
      servicesApi.route(servisId, 'aksam' satisfies Sefer),
    ])
      .then(([sabahRota, aksamRota]) => {
        if (iptal) return
        setSabah(duragiBul(sabahRota.stops))
        setAksam(duragiBul(aksamRota.stops))
      })
      .catch(() => {
        if (!iptal) setHata('Servis saatleri alınamadı.')
      })
      .finally(() => {
        if (!iptal) setYukleniyor(false)
      })

    return () => {
      iptal = true
    }
  }, [employeeId, servisId])

  const renk = servisId != null ? serviceColor(servisId) : '#64748b'

  return (
    <DialogPrimitive.Root
      open={employee !== null}
      onOpenChange={onOpenChange}
    >
      <DialogPrimitive.Portal>
        {/* Arka planı bulanıklaştıran katman — kart öne çıksın. */}
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm" />

        <DialogPrimitive.Content className="fixed top-1/2 left-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-xl border border-border bg-card shadow-xl">
          {employee ? (
            <>
              <DialogPrimitive.Title className="sr-only">
                {employee.adSoyad} detayı
              </DialogPrimitive.Title>
              <DialogPrimitive.Description className="sr-only">
                Personelin iletişim bilgileri, servisi ve kapı önü saatleri.
              </DialogPrimitive.Description>

              <div
                className="h-1.5 w-full"
                style={{ backgroundColor: renk }}
                aria-hidden
              />

              <div className="space-y-4 p-5">
                <header className="flex items-start justify-between gap-3">
                  <div>
                    <h2 className="text-lg font-semibold">
                      {employee.adSoyad}
                    </h2>
                    <p className="text-sm text-muted-foreground">
                      {employee.cinsiyet} · {employee.yas} yaşında ·{' '}
                      {employee.ilce}
                    </p>
                  </div>
                  {servisId != null ? (
                    <Link
                      to={`/servis/${servisId}`}
                      className="shrink-0 rounded-md px-2 py-1 text-sm font-medium hover:underline"
                      style={{ color: renk }}
                    >
                      {servisAdlari[servisId] ?? 'Servis'}
                    </Link>
                  ) : (
                    <Badge variant="outline">Servis atanmadı</Badge>
                  )}
                </header>

                <dl className="space-y-2 text-sm">
                  <Satir icon={<PhoneIcon className="size-3.5" />} label="Telefon">
                    {employee.telefon ?? (
                      <span className="text-muted-foreground">Kayıtlı değil</span>
                    )}
                  </Satir>
                  <Satir icon={<MapPinIcon className="size-3.5" />} label="Adres">
                    {employee.adres}
                  </Satir>
                </dl>

                <div className="flex flex-wrap gap-1.5">
                  {employee.arabaliMi ? (
                    <Badge variant="secondary">
                      <CarIcon className="size-3" />
                      Kendi aracı var
                    </Badge>
                  ) : null}
                  {employee.cocukVarMi ? (
                    <Badge variant="secondary">
                      <BabyIcon className="size-3" />
                      Çocuğu var
                    </Badge>
                  ) : null}
                </div>

                <section className="space-y-2">
                  <h3 className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
                    Kapı önü saatleri
                  </h3>

                  {servisId == null ? (
                    <p className="rounded-lg border border-border p-3 text-sm text-muted-foreground">
                      Kişi bir servise atanmadığı için saat hesaplanamıyor.
                    </p>
                  ) : yukleniyor ? (
                    <div className="flex items-center gap-2 rounded-lg border border-border p-3 text-sm text-muted-foreground">
                      <Loader2Icon className="size-4 animate-spin" />
                      Hesaplanıyor…
                    </div>
                  ) : hata ? (
                    <p className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
                      {hata}
                    </p>
                  ) : (
                    <div className="grid grid-cols-2 gap-2">
                      <SeferKutusu
                        icon={<SunIcon className="size-3.5" />}
                        baslik="Sabah"
                        aciklama="servis geliyor"
                        durak={sabah}
                      />
                      <SeferKutusu
                        icon={<MoonIcon className="size-3.5" />}
                        baslik="Akşam"
                        aciklama="evde"
                        durak={aksam}
                      />
                    </div>
                  )}
                </section>

                <LocationMiniMap
                  lat={employee.lat}
                  lon={employee.lon}
                  color={renk}
                  className="h-40"
                />
              </div>

              <DialogPrimitive.Close
                className="absolute top-4 right-4 rounded-sm bg-card/80 p-0.5 opacity-70 transition-opacity hover:opacity-100 focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none"
                aria-label="Kapat"
              >
                <XIcon className="size-4" />
              </DialogPrimitive.Close>
            </>
          ) : null}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}

function Satir({
  icon,
  label,
  children,
}: {
  icon: React.ReactNode
  label: string
  children: React.ReactNode
}) {
  return (
    <div className="flex gap-2">
      <dt className="flex w-20 shrink-0 items-center gap-1.5 text-muted-foreground">
        {icon}
        {label}
      </dt>
      <dd className="min-w-0 flex-1">{children}</dd>
    </div>
  )
}

/**
 * Varış saati tek dakika değil aralık: genişliği İBB verisinin günden güne
 * oynaklığından geliyor ve tur ilerledikçe birikiyor. İki uç eşitse tek saat
 * yazılır.
 */
function SeferKutusu({
  icon,
  baslik,
  aciklama,
  durak,
}: {
  icon: React.ReactNode
  baslik: string
  aciklama: string
  durak: Durak
}) {
  const saat = durak
    ? durak.varisSaatiErken === durak.varisSaatiGec
      ? durak.varisSaatiErken
      : `${durak.varisSaatiErken}–${durak.varisSaatiGec}`
    : '—'

  return (
    <div className="rounded-lg border border-border p-3">
      <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
        {icon}
        {baslik}
      </p>
      <p className="mt-1 text-lg font-semibold tabular-nums">{saat}</p>
      <p className="text-xs text-muted-foreground">
        {durak ? `${aciklama} · araçta ${durak.yolculukDk} dk` : aciklama}
      </p>
    </div>
  )
}
