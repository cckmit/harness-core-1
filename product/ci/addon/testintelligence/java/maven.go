package java

import (
	"context"
	"fmt"
	"github.com/wings-software/portal/commons/go/lib/exec"
	"github.com/wings-software/portal/commons/go/lib/filesystem"
	"github.com/wings-software/portal/product/ci/ti-service/types"
	"go.uber.org/zap"
	"regexp"
	"strings"
)

var (
	mavenCmd = "mvn"
)

type mavenRunner struct {
	fs                filesystem.FileSystem
	log               *zap.SugaredLogger
	cmdContextFactory exec.CmdContextFactory
}

func NewMavenRunner(log *zap.SugaredLogger, fs filesystem.FileSystem, factory exec.CmdContextFactory) *mavenRunner {
	return &mavenRunner{
		fs:                fs,
		log:               log,
		cmdContextFactory: factory,
	}
}

func (b *mavenRunner) AutoDetectPackages() ([]string, error) {
	return DetectPkgs(b.log, b.fs)
}

func (m *mavenRunner) GetCmd(ctx context.Context, tests []types.RunnableTest, userArgs, agentConfigPath string, ignoreInstr, runAll bool) (string, error) {
	// If instrumentation needs to be ignored, we run all the tests without adding the agent config
	if ignoreInstr {
		return strings.TrimSpace(fmt.Sprintf("%s %s", mavenCmd, userArgs)), nil
	}

	agentArg := fmt.Sprintf(javaAgentArg, agentConfigPath)
	instrArg := agentArg
	re := regexp.MustCompile(`(-Duser\.\S*)`)
	s := re.FindAllString(userArgs, -1)
	if s != nil {
		// If user args are present, move them to instrumentation
		userArgs = re.ReplaceAllString(userArgs, "")                        // Remove from arg
		instrArg = fmt.Sprintf("\"%s %s\"", strings.Join(s, " "), agentArg) // Add to instrumentation
	}
	if runAll {
		// Run all the tests
		return strings.TrimSpace(fmt.Sprintf("%s -am -DargLine=%s %s", mavenCmd, instrArg, userArgs)), nil
	}
	if len(tests) == 0 {
		return fmt.Sprintf("echo \"Skipping test run, received no tests to execute\""), nil
	}
	// Use only unique <package, class> tuples
	set := make(map[types.RunnableTest]interface{})
	ut := []string{}
	for _, t := range tests {
		w := types.RunnableTest{Pkg: t.Pkg, Class: t.Class}
		if _, ok := set[w]; ok {
			// The test has already been added
			continue
		}
		set[w] = struct{}{}
		if t.Pkg != "" {
			ut = append(ut, t.Pkg+"."+t.Class) // We should always have a package name. If not, use class to run
		} else {
			ut = append(ut, t.Class)
		}
	}
	testStr := strings.Join(ut, ",")
	return strings.TrimSpace(fmt.Sprintf("%s -Dtest=%s -am -DargLine=%s %s", mavenCmd, testStr, instrArg, userArgs)), nil
}