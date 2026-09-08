import { Toaster as Sonner } from 'sonner'

function Toaster() {
  return (
    <Sonner
      position="bottom-right"
      richColors
      closeButton
      toastOptions={{
        classNames: {
          toast: 'rounded-lg border border-border shadow-md',
        },
      }}
    />
  )
}

export { Toaster }
