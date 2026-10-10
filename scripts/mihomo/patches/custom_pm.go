package sing_tun

import (
	"encoding/json"
	"sync"

	tun "github.com/metacubex/sing-tun"

	"github.com/metacubex/mihomo/log"
)

// PackageEntry represents a single Android package with its UID,
// passed from the Java/Kotlin layer via Android's PackageManager API.
type PackageEntry struct {
	PackageName string `json:"packageName"`
	UID         uint32 `json:"uid"`
}

// androidAPIPackageManager implements tun.PackageManager using data
// provided by the Android SDK instead of reading /data/system/packages.xml.
type androidAPIPackageManager struct {
	idByPackage     map[string]uint32
	sharedByPackage map[string]uint32
	packageById     map[uint32][]string
	sharedById      map[uint32]string
}

func (m *androidAPIPackageManager) Start() error {
	return nil // data is already populated
}

func (m *androidAPIPackageManager) Close() error {
	return nil
}

func (m *androidAPIPackageManager) IDByPackage(packageName string) (uint32, bool) {
	id, ok := m.idByPackage[packageName]
	return id, ok
}

func (m *androidAPIPackageManager) IDBySharedPackage(sharedPackage string) (uint32, bool) {
	id, ok := m.sharedByPackage[sharedPackage]
	return id, ok
}

func (m *androidAPIPackageManager) PackageByID(id uint32) (string, bool) {
	names, ok := m.packageById[id]
	if !ok || len(names) == 0 {
		return "", false
	}
	return names[0], true
}

func (m *androidAPIPackageManager) PackagesByID(id uint32) ([]string, bool) {
	names, ok := m.packageById[id]
	return names, ok
}

func (m *androidAPIPackageManager) SharedPackageByID(id uint32) (string, bool) {
	name, ok := m.sharedById[id]
	return name, ok
}

// Global storage for the custom PM set from Java side
var (
	externalPM     tun.PackageManager
	externalPMLock sync.Mutex
)

// SetCustomPackageList parses a JSON array of PackageEntry objects from the
// Android SDK's PackageManager API and builds a custom tun.PackageManager.
// This must be called BEFORE StartWithFd so the TUN can use it.
func SetCustomPackageList(jsonData string) error {
	var entries []PackageEntry
	if err := json.Unmarshal([]byte(jsonData), &entries); err != nil {
		return err
	}

	pm := &androidAPIPackageManager{
		idByPackage:     make(map[string]uint32),
		sharedByPackage: make(map[string]uint32),
		packageById:     make(map[uint32][]string),
		sharedById:      make(map[uint32]string),
	}

	for _, entry := range entries {
		pm.idByPackage[entry.PackageName] = entry.UID
		pm.packageById[entry.UID] = append(pm.packageById[entry.UID], entry.PackageName)
	}

	externalPMLock.Lock()
	externalPM = pm
	externalPMLock.Unlock()

	log.Infoln("[TUN] Custom PackageManager loaded with %d packages from Android SDK", len(entries))
	return nil
}

// GetExternalPackageManager returns the externally-set PM, if any.
func GetExternalPackageManager() tun.PackageManager {
	externalPMLock.Lock()
	defer externalPMLock.Unlock()
	return externalPM
}
