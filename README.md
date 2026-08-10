# 💕 Tela de Bloqueio — Erica & Erick

Uma tela de bloqueio romântica para o casal **Erica Guarnieri** e **Erick Latocheski**, juntos desde **01/02/2025**.

## O que ela mostra

- 📷 A **foto do casal** como plano de fundo
- 🕐 **Hora real** do celular (relógio grande, atualiza a cada segundo)
- 📅 **Data real** por extenso (ex: "segunda-feira, 10 de agosto de 2026")
- 💞 **Tempo de namoro** em anos, meses e dias
- ⏱️ **Contador ao vivo** embaixo, contando dias, horas, minutos e segundos em tempo real
- ❤️ Nomes do casal com coração pulsando

## Como colocar a foto

Você tem **duas opções**:

**Opção 1 — pelo próprio app (mais fácil):**
1. Abra o `index.html` no celular
2. Toque no botão 📷 no canto superior direito
3. Escolha a foto na galeria — ela fica salva no aparelho

**Opção 2 — arquivo fixo:**
1. Salve a foto do casal como `foto.jpg` nesta mesma pasta
2. Pronto — ela aparece automaticamente

## Como usar como tela de bloqueio / app no celular

1. Abra o `index.html` no navegador do celular (Chrome ou Safari)
2. Toque no menu do navegador → **"Adicionar à tela inicial"**
3. Abra pelo ícone criado — abre em **tela cheia**, parecendo um app de verdade

> Observação: por segurança, um site não substitui a tela de bloqueio nativa do Android/iOS. Este app funciona como um "relógio/tela do casal" em tela cheia. Para usar como papel de parede, você também pode tirar um print e definir como wallpaper. 💕

## Personalizar

No arquivo `index.html`, no início do `<script>`:

```js
var START_DATE = new Date(2025, 1, 1, 0, 0, 0); // 01/02/2025 (mês começa em 0: 1 = fevereiro)
```

Altere a data se quiser. Os nomes estão logo no HTML, na `<div class="couple">`.
