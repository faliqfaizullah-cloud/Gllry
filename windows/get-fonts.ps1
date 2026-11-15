# Windows PowerShell: downloads the UI fonts into .\fonts   (run:  powershell -ExecutionPolicy Bypass -File get-fonts.ps1)
New-Item -ItemType Directory -Force -Path fonts | Out-Null
$g = "https://github.com/google/fonts/raw/main/ofl"
Invoke-WebRequest "$g/instrumentsans/InstrumentSans%5Bwdth%2Cwght%5D.ttf" -OutFile fonts\ui.ttf
Invoke-WebRequest "$g/doto/Doto%5BROND%2Cwght%5D.ttf" -OutFile fonts\pixel.ttf
Invoke-WebRequest "$g/gaegu/Gaegu-Regular.ttf" -OutFile fonts\script.ttf
Get-ChildItem fonts
