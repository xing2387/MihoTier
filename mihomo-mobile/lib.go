package mobile

import (
	"fmt"
	"log"
	"os"

	"gopkg.in/yaml.v3"
	"github.com/sirupsen/logrus"

	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/tunnel/statistic"
	singtun "github.com/metacubex/mihomo/listener/sing_tun"
)

// SetPackageList receives a JSON array of {packageName, uid} objects from
// Android's PackageManager API. Must be called BEFORE StartWithFd.
func SetPackageList(jsonData string) error {
	return singtun.SetCustomPackageList(jsonData)
}

// Start initializes and starts the Mihomo core
func Start(homeDir string, configStr string) error {
	C.SetHomeDir(homeDir)
	return hub.Parse([]byte(configStr))
}

// TestLogToLogcat is a simple function exposed to Android to test JNI logging
func TestLogToLogcat(msg string) {
	// gomobile automatically redirects standard output and the standard log package to Android Logcat
	fmt.Println("Go (fmt): This is a test message ->", msg)
	log.Println("Go (log): This is a test message ->", msg)
}

func StartWithFd(homeDir string, configStr string, fd int) error {
	C.SetHomeDir(homeDir)

	// Instead of a file, we can redirect logrus to standard output/log which gomobile forwards to logcat!
	logrus.SetOutput(os.Stdout)
	log.Println("Mihomo StartWithFd called in Go!", homeDir)

	
	// Robustly inject DNS config by parsing YAML to override user's DNS settings (e.g. dns: enable: false)
	var raw map[string]interface{}
	if err := yaml.Unmarshal([]byte(configStr), &raw); err == nil {
		if raw == nil {
			raw = make(map[string]interface{})
		}
		raw["dns"] = map[string]interface{}{
			"enable": true,
			"listen": "0.0.0.0:1053",
			"enhanced-mode": "fake-ip",
			"fake-ip-range": "198.18.0.1/16",
			"nameserver": []interface{}{
				"114.114.114.114",
				"8.8.8.8",
			},
		}
		if out, err := yaml.Marshal(raw); err == nil {
			configStr = string(out)
		}
	}

	return hub.Parse([]byte(configStr), func(cfg *config.Config) {
		cfg.Controller.ExternalController = "127.0.0.1:9090"
		cfg.General.Tun.Enable = true
		cfg.General.Tun.Stack = C.TunGvisor
		cfg.General.Tun.FileDescriptor = fd
		cfg.General.Tun.AutoRoute = false
		cfg.General.Tun.AutoDetectInterface = false
		cfg.General.Tun.DNSHijack = []string{"any:53"}
// 		cfg.General.LogLevel = mlog.DEBUG

		// Inject fast mirrors for Geo databases to avoid startup timeout
		cfg.General.GeoXUrl.GeoIp = "https://mirror.ghproxy.com/https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb"
		cfg.General.GeoXUrl.GeoSite = "https://mirror.ghproxy.com/https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat"
		cfg.General.GeoXUrl.Mmdb = "https://mirror.ghproxy.com/https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/country.mmdb"
	})
}

// Stop shuts down the Mihomo core
func Stop() {
	executor.Shutdown()
}

// QueryTrafficUp returns the total uploaded bytes
func QueryTrafficUp() int64 {
	if statistic.DefaultManager != nil {
		up, _ := statistic.DefaultManager.Total()
		return up
	}
	return 0
}

// QueryTrafficDown returns the total downloaded bytes
func QueryTrafficDown() int64 {
	if statistic.DefaultManager != nil {
		_, down := statistic.DefaultManager.Total()
		return down
	}
	return 0
}

// QueryTrafficNowUp returns the instantaneous upload speed (bytes/sec)
func QueryTrafficNowUp() int64 {
	if statistic.DefaultManager != nil {
		up, _ := statistic.DefaultManager.Now()
		return up
	}
	return 0
}

// QueryTrafficNowDown returns the instantaneous download speed (bytes/sec)
func QueryTrafficNowDown() int64 {
	if statistic.DefaultManager != nil {
		_, down := statistic.DefaultManager.Now()
		return down
	}
	return 0
}
