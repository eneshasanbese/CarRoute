import type { Table } from '@tanstack/react-table'
import {
  ArrowUpDownIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { TUM_DEGERLER } from '@/lib/tableFilters'

/** Personel ve şoför tablolarının ortak parçaları. */

export function SortableHeader({
  column,
  children,
}: {
  column: { toggleSorting: (desc?: boolean) => void; getIsSorted: () => false | 'asc' | 'desc' }
  children: ReactNode
}) {
  return (
    <button
      type="button"
      className="inline-flex items-center gap-1 uppercase hover:text-foreground"
      onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
    >
      {children}
      <ArrowUpDownIcon className="size-3" />
    </button>
  )
}

export function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  options: Array<{ value: string; label: string }>
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className="w-auto min-w-36">
        <SelectValue placeholder={label} />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={TUM_DEGERLER}>{label}: hepsi</SelectItem>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

/** Kayıt sayısı ve sayfa düğmeleri. */
export function TablePagination<T>({
  table,
  toplam,
  filtreVar,
}: {
  table: Table<T>
  /** Filtre uygulanmadan önceki kayıt sayısı. */
  toplam: number
  filtreVar: boolean
}) {
  return (
    <div className="flex items-center justify-between text-sm text-muted-foreground">
      <span>
        {table.getFilteredRowModel().rows.length} kayıt
        {filtreVar ? ` (toplam ${toplam})` : ''}
      </span>
      <div className="flex items-center gap-2">
        <span className="tabular-nums">
          Sayfa {table.getState().pagination.pageIndex + 1} /{' '}
          {Math.max(1, table.getPageCount())}
        </span>
        <Button
          variant="outline"
          size="icon"
          aria-label="Önceki sayfa"
          onClick={() => table.previousPage()}
          disabled={!table.getCanPreviousPage()}
        >
          <ChevronLeftIcon />
        </Button>
        <Button
          variant="outline"
          size="icon"
          aria-label="Sonraki sayfa"
          onClick={() => table.nextPage()}
          disabled={!table.getCanNextPage()}
        >
          <ChevronRightIcon />
        </Button>
      </div>
    </div>
  )
}
