import { useDispatch, useSelector } from 'react-redux'
import type { AppDispatch, RootState } from '@/store'

/** Tipli dispatch/selector — bileşenlerde bunlar kullanılır. */
export const useAppDispatch = useDispatch.withTypes<AppDispatch>()
export const useAppSelector = useSelector.withTypes<RootState>()
