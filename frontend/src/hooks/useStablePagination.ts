import type { PaginationState, Table } from '@tanstack/react-table'
import { useCallback, useEffect, useState } from 'react'

/**
 * Veri tazelendiğinde yerinde kalan tablo sayfalaması.
 *
 * TanStack Table varsayılan olarak `data` her değiştiğinde sayfayı başa sarıyor.
 * Listeler usePolling ile 5 sn'de bir yeniden çekildiği için bu, sayfalar
 * arasında gezinen kullanıcıyı sürekli ilk sayfaya atıyordu. Tabloya
 * `autoResetPageIndex: false` verilip sayfa burada tutuluyor; başa dönüş yalnızca
 * arama, filtre ya da sıralama değişince `resetPageIndex` ile yapılıyor.
 */
export function useStablePagination(pageSize: number) {
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize,
  })

  const resetPageIndex = useCallback(() => {
    setPagination((current) =>
      current.pageIndex === 0 ? current : { ...current, pageIndex: 0 },
    )
  }, [])

  return { pagination, setPagination, resetPageIndex }
}

/**
 * Kayıt silinip sayfa sayısı azaldığında boş bir sayfada kalmamak için son
 * sayfaya çeker. Otomatik sıfırlama kapalı olduğundan bunu tablo kendisi yapmıyor.
 */
export function usePageIndexClamp<T>(table: Table<T>) {
  const pageCount = table.getPageCount()
  const { pageIndex } = table.getState().pagination

  useEffect(() => {
    if (pageCount > 0 && pageIndex >= pageCount) {
      table.setPageIndex(pageCount - 1)
    }
  }, [table, pageCount, pageIndex])
}
