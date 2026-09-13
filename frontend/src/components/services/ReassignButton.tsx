import { Loader2Icon, ShuffleIcon } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { fetchEmployees } from '@/store/employeesSlice'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { reassignAll } from '@/store/servicesSlice'

/**
 * Bütün personeli sıfırdan dağıtır.
 *
 * <p>
 * Personel eklendiğinde/silindiğinde çalışan dengeleme mevcut dağılımı çıpa alır
 * ve yalnızca kazandıran hamleleri yapar; kimsenin servisi durduk yere
 * değişmesin diye. Bunun bedeli zamanla birikir: arka arkaya yapılan yerel
 * düzeltmeler tabloyu, baştan kurulsa bulunamayacak bir yerel çukurda bırakır.
 * Bu düğme çıpayı kaldırır.
 *
 * <p>
 * Geri alınamadığı ve neredeyse herkesin servisini değiştirebildiği için önce
 * onay sorulur.
 */
export function ReassignButton() {
  const dispatch = useAppDispatch()
  const calisiyor = useAppSelector((state) => state.services.reassigning)
  const [acik, setAcik] = useState(false)

  const dagit = async () => {
    try {
      const sonuc = await dispatch(reassignAll()).unwrap()

      // Personel kayıtları servis numarasını taşıyor; liste ekranı bayatlamasın.
      void dispatch(fetchEmployees())

      if (sonuc.servisiDegisen === 0) {
        // Backend yeni tabloyu yalnızca kazandırıyorsa kabul ediyor.
        toast.info('Mevcut dağılım korundu', {
          description:
            'Baştan kurulan tablo bugünkünden iyi çıkmadı, kimsenin servisi değişmedi.',
        })
        return
      }

      const ihlal = sonuc.servisler.filter((s) => s.kuralIhlali).length
      toast.success('Personel yeniden dağıtıldı', {
        description:
          `${sonuc.toplamPersonel} kişiden ${sonuc.servisiDegisen} kişinin servisi değişti. ` +
          (ihlal > 0
            ? `${ihlal} servis süre sınırını aşıyor.`
            : 'Hiçbir servis süre sınırını aşmıyor.'),
      })
    } catch (mesaj) {
      toast.error('Dağıtım yapılamadı', { description: String(mesaj) })
    }
  }

  return (
    <AlertDialog open={acik} onOpenChange={setAcik}>
      <Button
        variant="outline"
        size="sm"
        disabled={calisiyor}
        onClick={() => setAcik(true)}
      >
        {calisiyor ? (
          <Loader2Icon className="animate-spin" />
        ) : (
          <ShuffleIcon />
        )}
        {calisiyor ? 'Dağıtılıyor…' : 'Yeniden dağıt'}
      </Button>

      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Personel yeniden dağıtılsın mı?</AlertDialogTitle>
          <AlertDialogDescription>
            Bütün atamalar sıfırlanır ve herkes baştan yerleştirilir; dağıtım
            sabah ve akşam seferini birlikte ölçer, 90 dakika kuralını ve araç
            kapasitesini gözetir. Yeni tablo bugünkünden iyi çıkmazsa kabul
            edilmez — ama iyi çıkarsa çoğu kişinin servisi değişebilir ve işlem
            geri alınamaz.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Vazgeç</AlertDialogCancel>
          <AlertDialogAction onClick={() => void dagit()}>
            Dağıt
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
