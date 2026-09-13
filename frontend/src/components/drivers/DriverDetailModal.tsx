import * as DialogPrimitive from '@radix-ui/react-dialog'
import { BusIcon, MapPinIcon, PhoneIcon, XIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { LocationMiniMap } from '@/components/map/LocationMiniMap'
import { CapacityGauge } from '@/components/services/CapacityGauge'
import { Badge } from '@/components/ui/badge'
import { useServiceColor } from '@/hooks/useServiceColor'
import { servisAdi } from '@/lib/serviceName'
import type { Driver, Service } from '@/types'

interface DriverDetailModalProps {
  driver: Driver | null
  /** Şoförün sürdüğü servis; doluluğu göstermek için. Yüklenmediyse boş. */
  service: Service | undefined
  onOpenChange: (open: boolean) => void
}

/**
 * Şoför kartı: iletişim bilgileri, aracı, sürdüğü servisin doluluğu ve ev
 * adresi. Rota sabah bu adresten başlayıp akşam burada bittiği için konum da
 * haritada gösteriliyor.
 */
export function DriverDetailModal({
  driver,
  service,
  onOpenChange,
}: DriverDetailModalProps) {
  const serviceColor = useServiceColor()
  const servisId = driver?.servisId ?? null
  const renk = servisId != null ? serviceColor(servisId) : '#64748b'

  return (
    <DialogPrimitive.Root open={driver !== null} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        {/* Arka planı bulanıklaştıran katman — kart öne çıksın. */}
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm" />

        <DialogPrimitive.Content className="fixed top-1/2 left-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-xl border border-border bg-card shadow-xl">
          {driver ? (
            <>
              <DialogPrimitive.Title className="sr-only">
                {driver.adSoyad} detayı
              </DialogPrimitive.Title>
              <DialogPrimitive.Description className="sr-only">
                Şoförün iletişim bilgileri, aracı ve sürdüğü servis.
              </DialogPrimitive.Description>

              <div
                className="h-1.5 w-full"
                style={{ backgroundColor: renk }}
                aria-hidden
              />

              <div className="space-y-4 p-5">
                <header className="flex items-start justify-between gap-3">
                  <div>
                    <h2 className="text-lg font-semibold">{driver.adSoyad}</h2>
                    <p className="text-sm text-muted-foreground">
                      Şoför · {driver.ilce}
                    </p>
                  </div>
                  {servisId != null ? (
                    <Link
                      to={`/servis/${servisId}`}
                      className="shrink-0 rounded-md px-2 py-1 text-sm font-medium hover:underline"
                      style={{ color: renk }}
                    >
                      {servisAdi(driver.plaka)}
                    </Link>
                  ) : (
                    <Badge variant="outline">Servisi yok</Badge>
                  )}
                </header>

                <dl className="space-y-2 text-sm">
                  <Satir icon={<PhoneIcon className="size-3.5" />} label="Telefon">
                    {driver.telefon ?? <Bos>Kayıtlı değil</Bos>}
                  </Satir>
                  <Satir icon={<MapPinIcon className="size-3.5" />} label="Adres">
                    {driver.adres}
                  </Satir>
                  <Satir icon={<BusIcon className="size-3.5" />} label="Araç">
                    {aracMetni(driver) ?? <Bos>Kayıtlı değil</Bos>}
                  </Satir>
                </dl>

                {service ? (
                  <section className="space-y-2">
                    <h3 className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
                      Servis doluluğu
                    </h3>
                    <div className="rounded-lg border border-border p-3">
                      <CapacityGauge service={service} />
                    </div>
                  </section>
                ) : null}

                <LocationMiniMap
                  lat={driver.lat}
                  lon={driver.lon}
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

/**
 * "Mercedes Sprinter · 15 kişilik"; hiçbiri yoksa null. Plaka burada yok çünkü
 * başlıkta servisin adı olarak zaten yazıyor.
 */
function aracMetni(driver: Driver) {
  const parcalar = [
    driver.model,
    driver.kapasite > 0 ? `${driver.kapasite} kişilik` : null,
  ].filter(Boolean)
  return parcalar.length > 0 ? parcalar.join(' · ') : null
}

function Bos({ children }: { children: ReactNode }) {
  return <span className="text-muted-foreground">{children}</span>
}

function Satir({
  icon,
  label,
  children,
}: {
  icon: ReactNode
  label: string
  children: ReactNode
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
