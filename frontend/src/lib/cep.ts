/** Endereço encontrado para um CEP. Campos podem vir vazios (ex.: CEP geral de uma cidade pequena). */
export interface CepAddress {
  street: string
  neighborhood: string
  city: string
}

const TIMEOUT_MS = 5000

/**
 * Busca o endereço de um CEP na ViaCEP (https://viacep.com.br), um serviço público e gratuito.
 *
 * - Retorna `null` quando o CEP não existe.
 * - Lança erro quando o serviço não responde a tempo ou está fora do ar.
 *
 * É só uma ajuda para preencher o formulário: quem chama deve deixar a pessoa
 * digitar o endereço à mão se a busca falhar.
 */
export async function lookupCep(cep: string): Promise<CepAddress | null> {
  const digits = cep.replace(/\D/g, '')
  if (digits.length !== 8) return null

  const response = await fetch(`https://viacep.com.br/ws/${digits}/json/`, {
    signal: AbortSignal.timeout(TIMEOUT_MS),
  })
  if (!response.ok) throw new Error(`ViaCEP respondeu HTTP ${response.status}`)

  const data = await response.json()
  // CEP com formato válido mas inexistente: a ViaCEP responde 200 com { "erro": true }
  if (data.erro) return null

  return {
    street: data.logradouro ?? '',
    neighborhood: data.bairro ?? '',
    city: data.localidade ?? '',
  }
}
