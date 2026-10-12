import type { ReactNode } from "react";
import clsx from "clsx";
import Heading from "@theme/Heading";
import styles from "./styles.module.css";

type FeatureItem = {
  title: string;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: "UniFFI Bindings Generation for Kotlin Multiplatform",
    description: (
      <>
        Rustine generates Kotlin code that connects your project to Rust code
        using <a href="https://github.com/mozilla/uniffi-rs">UniFFI</a>. You
        don't need to create your own wrapper manually, as you did with JNI.
      </>
    ),
  },
  {
    title: "KotlinX Serialization Support",
    description: (
      <>
        Rustine detects the presence of KotlinX Serialization in your Gradle
        project and annotates data classes generated from Rust structs with{" "}
        <code>@kotlinx.Serializable</code>.
      </>
    ),
  },
  {
    title: "Easy-to-Use Cargo Integration",
    description: (
      <>
        Rustine automatically invokes Cargo as you build your Kotlin
        Multiplatform project. Required environment variables for each platform
        are automatically configured.
      </>
    ),
  },
];

function Feature({ title, description }: FeatureItem) {
  return (
    <div className={clsx("col col--4")}>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
