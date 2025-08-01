# 🖥️ WT Display Tester

- Aplicativo Java Swing para teste de comunicação com display TOTVS via Serial (RS232/RS485) ou TCP/IP.
- Permite enviar comandos, visualizar logs, exportar para PDF e salvar configurações de teste.

# ✨ Funcionalidades principais

✅ Detecção automática de portas seriais
✅ Envio de comandos customizados ou padrões (CommandService)
✅ Logging detalhado (INFO, DEBUG, ERROR) com arquivos de log (LogService)
✅ Leitura assíncrona em thread para Serial (SerialService) e TCP (TcpService)
✅ Exportação de logs para PDF
✅ Configurações persistentes (ConfigManager)

## 🛠️ Estrutura do projeto

Pacote / Classe             Função

- Main                        Ponto de entrada da aplicação
- gui.MainFrame               Interface Swing (JFrame) com botões, campos e logs
- service.SerialService       Comunicação serial: conecta, envia, lê dados em thread
- service.TcpService          Comunicação TCP/IP: conecta, envia, lê dados em thread
- service.CommandService      Geração de comandos padrão para o dide onde enviar (Serial ou TCP)
- service.LogService          Decide onde enviar (Serial ou TCP)
- service.ConnectionManager   Gerencia config.properties
- model.ConfigManager        Salva e carrega configurações em config.properties


## 🧰 Tecnologias usadas

- Java 21
- Maven
- IDE (Eclipse ou IntelliJ) ou terminal
- jSerialComm – comunicação serial
- iTextPDF – exportação de logs em PDF (para futuras versões)

## 📦 Estrutura do projeto
pgsql
Copiar
Editar
testedisplay/
├── pom.xml
├── .project
├── .classpath
├── README.md
├── logs/                       ← arquivos de log gerados dinamicamente
└── src/
    └── main/
        └── java/com/example/testedisplay/
            ├── Main.java                          ← classe principal
            ├── gui/MainFrame.java                 ← interface Swing
            ├── model/ConfigManager.java           ← carrega/salva configurações
            └── service/
                ├── CommandService.java            ← gera comandos prontos
                ├── ConnectionManager.java         ← gerencia conexões Serial/TCP
                ├── LogService.java                ← registra logs com níveis
                ├── SerialService.java             ← comunicação Serial
                └── TcpService.java                ← comunicação TCP

## 🚀 Como compilar e executar

### Passo a passo no terminal

1. Clone ou extraia o projeto em sua máquina

git clone https://github.com/seuusuario/testedisplay.git
cd testedisplay

2. Compile e gere o jar:

mvn clean package
O jar será gerado em:
target/testedisplay-1.0.0-jar-with-dependencies.jar

Para rodar: java -jar target/testedisplay-1.0.0-jar-with-dependencies.jar

### Passo a passo no Eclipse

1. Clone ou extraia o projeto em sua máquina

File → Import → Existing Maven Project

Selecione a pasta onde está o pom.xml

Final
 
 ### pass a passo no intellij

1. Clone ou extraia o projeto em sua máquina

File → Open → Selecione a pasta onde está o pom.xml

Final

## 📡 Comunicação Serial & TCP

Serviço                 O que faz

SerialService           Conecta na porta (ex: COM3), cria thread que lê dados recebidos e loga, envia comandos em UTF-8
TcpService              Conecta no IP e porta (ex: 192.168.1.100:1234), cria thread que lê dados recebidos e loga, envia comandos em UTF-8

Todos os dados recebidos são registrados como DEBUG no log.

## 📝 Logs

LogService              Registra logs em arquivo e console, com níveis INFO, DEBUG e ERROR

Arquivo de log gerado em logs/testedisplay.log

## 🔧 Configurações

ConfigManager           Salva e carrega configura

 ## 📤 Exportação PDF

- Clica em Exportar Log para PDF na UI
- Gera PDF no mesmo diretório com timestamp no nome

## 🏁 Encerramento seguro

- Desconecta portas / sockets
- Interrompe threads leitoras
- Fecha arquivos de log
- Salva config.properties

## ✅ Conclusão

Projeto pronto para rodar localmente, documentado e modular.
Suporte a testes de display TOTVS com flexibilidade: Serial (RS232/RS485) ou TCP/IP.

Autor: Renato Félix
        Versão: 1.0.0

Dúvidas? Melhorias? Abra um issue ou envie pull request!

## 📝 Licença

Este projeto está sob a licença MIT. Consulte o arquivo LICENSE para obter mais detalhes.